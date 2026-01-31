package tech.dobler.werstreamt.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommonAttributeServiceTest {

    @Mock
    private ImdbEntryRepository imdbEntryRepositoryMock;

    @Mock
    private Model modelMock;

    @Test
    void add() {
        final var unitUnderTest = new CommonAttributeService(imdbEntryRepositoryMock);
        final var nameOfList = "some list name";
        when(imdbEntryRepositoryMock.getNameOfList()).thenReturn(nameOfList);

        unitUnderTest.add(modelMock);


        verify(imdbEntryRepositoryMock).getNameOfList();
        verify(modelMock).addAttribute("selectedList", nameOfList);
    }
}