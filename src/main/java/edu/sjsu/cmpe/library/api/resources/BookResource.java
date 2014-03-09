package edu.sjsu.cmpe.library.api.resources;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.yammer.dropwizard.jersey.params.LongParam;
import com.yammer.metrics.annotation.Timed;

import edu.sjsu.cmpe.library.domain.Author;
import edu.sjsu.cmpe.library.domain.Book;
import edu.sjsu.cmpe.library.domain.Review;
import edu.sjsu.cmpe.library.dto.AuthorDto;
import edu.sjsu.cmpe.library.dto.BookDto;
import edu.sjsu.cmpe.library.dto.LinkDto;
import edu.sjsu.cmpe.library.dto.ReviewDto;
import edu.sjsu.cmpe.library.repository.BookRepositoryInterface;

@Path("/v1/books")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class BookResource {
    /** bookRepository instance */
    private final BookRepositoryInterface bookRepository;

    /**
     * BookResource constructor
     * 
     * @param bookRepository
     *            a BookRepository instance
     */
    public BookResource(BookRepositoryInterface bookRepository) {
	this.bookRepository = bookRepository;
    }

    @GET
    @Path("/{isbn}")
    @Timed(name = "view-book")
    public Response viewBookbyISBN(@PathParam("isbn") LongParam isbn){
    	BookDto bookResponse = getBookByIsbn(isbn);
    	Book book = bookResponse.getBook();
    	
    	AuthorDto authorLinks = new AuthorDto();
		if(book.getAuthorList()!=null && !book.getAuthorList().isEmpty()){
			for(Author authorObj : book.getAuthorList()){
				authorLinks.addLink(new LinkDto("view-author", "/books/" + 
						book.getIsbn() + "/authors/" + authorObj.getId(), "GET"));
			}
		}
		
		ReviewDto reviewLinks = new ReviewDto();
		if(book.getReviewList()!=null && book.getReviewList().size() > 0){
			for(Review reviewObj : book.getReviewList()){
				reviewLinks.addLink(new LinkDto("update-book", "/books/" + book.getIsbn() + 
						"/reviews/" + reviewObj.getId(), "GET"));
			}
		}
		
		Map<String, Object> bookMap = new HashMap<String, Object>();
		bookMap.put("isbn", book.getIsbn());
		bookMap.put("title", book.getTitle());
		bookMap.put("publication-date", book.getPublication_date());
		bookMap.put("language", book.getLanguage());
		bookMap.put("num-pages", book.getNum_pages());
		bookMap.put("status", book.getStatus());
		bookMap.put("reviews", reviewLinks.getLinks());
		bookMap.put("authors", authorLinks.getLinks());
		
		
		Map<String, Object> displayMap = new HashMap<String, Object>();
		displayMap.put("book", bookMap);
		displayMap.put("links", bookResponse.getLinks());
		
		return Response.status(200).entity(displayMap).build();
    }
   
    public BookDto getBookByIsbn(@PathParam("isbn") LongParam isbn) {
		Book book = bookRepository.getBookByISBN(isbn.get());
		BookDto bookResponse = new BookDto(book);
		bookResponse.addLink(new LinkDto("view-book", "/books/" + book.getIsbn(), "GET"));
		bookResponse.addLink(new LinkDto("update-book", "/books/" + book.getIsbn(), "PUT"));
		
		// add more links
		bookResponse.addLink(new LinkDto("delete-book", "/books/" + book.getIsbn(), "DELETE"));
		bookResponse.addLink(new LinkDto("review-book", "/books/" + book.getIsbn() + "/reviews", "POST"));
	
		return bookResponse;
    }

    @POST
    @Timed(name = "create-book")
    public Response createBook(@Valid Book request) {
    	List<String> statusList = Arrays.asList("available", "check-out", "in-queue","lost");
    	String status = request.getStatus();
    	if(status == null || status.isEmpty()){
    		request.setStatus("available");;
    	}else if(!statusList.contains((status.toLowerCase()))){
    		return Response.status(422).type("text/plain")
    				.entity("Wrong status value. Status should be one of the follwoing: available, check-out, in-queue or lost")
    				.build();
    	}else{
    		request.setStatus(status.toLowerCase());
    	}
    	
    	if(request.getAuthorList() == null || request.getAuthorList().isEmpty()){
    		return Response.status(422).type("text/plain").entity("Author cannot be empty").build();
    	}else{
    		for(Author authorObj : request.getAuthorList()){
    			if(authorObj.getName()==null || authorObj.getName().length()==0){
    				return Response.status(422).type("text/plain").entity("Author Name cannot be empty").build();
    			}
    		}
    	}
    	
		// Store the new book in the BookRepository so that we can retrieve it.
		Book savedBook = bookRepository.saveBook(request);
	
		String location = "/books/" + savedBook.getIsbn();
		BookDto bookResponse = new BookDto(savedBook);
		bookResponse.addLink(new LinkDto("view-book", location, "GET"));
		bookResponse.addLink(new LinkDto("update-book", location, "PUT"));
		
		// Add other links if needed
		bookResponse.addLink(new LinkDto("delete-book", location, "DELETE"));
		bookResponse.addLink(new LinkDto("create-review", location + "/reviews", "POST"));
		
		/*Map<String, Object> resultMap = new HashMap<String, Object>();
		resultMap.put("links", bookResponse.getLinks());*/
		
		return Response.status(201).entity(createDisplayMap(bookResponse)).build();
    }
    
    @DELETE
    @Path("/{isbn}")
    @Timed(name = "delete-book")
    public Response deleteBook(@PathParam("isbn") LongParam isbn){
    	bookRepository.deleteBook(isbn.get());
    	BookDto bookResponse = new BookDto();
		bookResponse.addLink(new LinkDto("create-book", "/books", "POST"));
	
    	return Response.status(200).entity(createDisplayMap(bookResponse)).build();
    }
    
    /**
     * This method will create a Map o display the desired links to the user
     * @param bookResponse
     * @return
     */
    private Map<String, Object> createDisplayMap(BookDto bookResponse){
    	Map<String, Object> displayMap = new HashMap<String, Object>();
		displayMap.put("links", bookResponse.getLinks());
		return displayMap;
    }
    
    @PUT
    @Path("/{isbn}")
    @Timed(name = "update-book")
    public Response updateBookByIsbn(@PathParam("isbn") LongParam isbn, @QueryParam("status") String status){
    	List<String> statusList = Arrays.asList("available", "check-out", "in-queue","lost");
    	if(status == null || status.isEmpty()){
    		status = "available";
    	}else if(!statusList.contains(status.toLowerCase())){
    		return Response.status(422).type("text/plain").entity("Wrong status value").build();
    	}else{
    		status.toLowerCase();
    	}
    	Book updatedBook = bookRepository.updateBook(isbn.get(),status);
    	//update the links 
		BookDto bookResponse = new BookDto(updatedBook);
		bookResponse.addLink(new LinkDto("view-book", "/books/" + updatedBook.getIsbn(), "GET"));
		bookResponse.addLink(new LinkDto("update-book", "/books/" + updatedBook.getIsbn(), "PUT"));
		bookResponse.addLink(new LinkDto("delete-book", "/books/" + updatedBook.getIsbn(), "DELETE"));
		bookResponse.addLink(new LinkDto("create-review", "/books/" + updatedBook.getIsbn(), "POST"));
		if(updatedBook.getReviewList()!=null && !updatedBook.getReviewList().isEmpty()){
			bookResponse.addLink(new LinkDto("create-review", "/books/" + updatedBook.getIsbn(), "GET"));
		}
		
		/*Map<String, Object> displayMap = new HashMap<String, Object>();
		displayMap.put("links", bookResponse.getLinks());*/
		return Response.status(200).entity(createDisplayMap(bookResponse)).build();
    }
    
}

